# Deployment Guide

This app is split across two free hosts:

| Part | Host | Cost |
|------|------|------|
| Frontend (React) | GitHub Pages | Free |
| Backend + DB + AI | Oracle Cloud Always Free | Free |

---

## Part 1 — Oracle Cloud (backend)

### 1.1 Create a free Oracle Cloud account

1. Go to [cloud.oracle.com](https://cloud.oracle.com) and sign up for a **Free Tier** account.
2. Choose a **home region** close to your users (you cannot change it later).
3. A credit card is required for verification but **you will not be charged** for Always Free resources.

### 1.2 Create an Always Free VM

Oracle Always Free gives you one ARM Ampere A1 instance with up to **4 OCPUs and 24 GB RAM** — more than enough for this app.

1. In the Oracle Console, go to **Compute → Instances → Create Instance**.
2. Click **Change shape** → select **Ampere** → **VM.Standard.A1.Flex**.
3. Set **4 OCPUs** and **24 GB RAM** (within the Always Free limit).
4. Under **Image**, choose **Ubuntu 22.04 (aarch64)**.
5. Under **Networking**, make sure a **public subnet** is selected and **Assign a public IPv4** is checked.
6. **Add your SSH public key** (generate one with `ssh-keygen -t ed25519` if you don't have one).
7. Click **Create**.

Note the **Public IP address** of your VM — you will need it throughout this guide.

### 1.3 Open firewall ports in Oracle Console

Oracle has two layers of firewall: the VCN Security List and the OS firewall. Do both.

**VCN Security List (Oracle Console):**

1. Go to **Networking → Virtual Cloud Networks → your VCN → Security Lists → Default Security List**.
2. Add **Ingress Rules**:
   - Source: `0.0.0.0/0`, Protocol: TCP, Port: **80** (HTTP)
   - Source: `0.0.0.0/0`, Protocol: TCP, Port: **443** (HTTPS, for future use)

**OS firewall (on the VM):**

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

### 1.4 Install Docker and Docker Compose on the VM

SSH into your VM:

```bash
ssh ubuntu@<YOUR_VM_PUBLIC_IP>
```

Install Docker:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
# Log out and back in for the group change to take effect
exit
ssh ubuntu@<YOUR_VM_PUBLIC_IP>
```

Verify:

```bash
docker run hello-world
```

### 1.5 Clone your repository on the VM

```bash
git clone https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>.git
cd <YOUR_REPO_NAME>
```

### 1.6 Create the `.env` file

Copy the example and fill in your real secrets:

```bash
cp .env.example .env
nano .env
```

Fill in:

```
GOOGLE_CLIENT_SECRET=<your Google OAuth client secret>
DRIVE_REFRESH_TOKEN=<your Google Drive refresh token>
DB_PASSWORD=<choose a strong password>
AI_SERVICE_INTERNAL_API_KEY=<choose a random secret key>
```

### 1.7 Update the auth service CORS URL

In `docker-compose.yml`, update the `auth-service` environment to point to your GitHub Pages URL:

```yaml
auth-service:
  environment:
    APP_SECURITY_FRONTEND_URL: https://<YOUR_GITHUB_USERNAME>.github.io
```

### 1.8 Start the application

```bash
docker compose up -d --build
```

The first start takes 10–20 minutes because the AI service downloads ~5 GB of model weights. Monitor progress:

```bash
docker compose logs -f ai-service
```

Once healthy, the app is reachable at `http://<YOUR_VM_PUBLIC_IP>`.

### 1.9 (Optional) Set up a domain with HTTPS

If you have a domain, point an A record to your VM IP, then add Traefik ACME / Let's Encrypt config. This is out of scope for this guide but Traefik has built-in Let's Encrypt support.

---

## Part 2 — GitHub Pages (frontend)

### 2.1 Enable GitHub Pages in your repo

1. Go to your GitHub repo → **Settings → Pages**.
2. Under **Source**, select **GitHub Actions**.

### 2.2 Set the required secret

1. Go to **Settings → Secrets and variables → Actions → New repository secret**.
2. Name: `VITE_API_URL`
3. Value: `http://<YOUR_VM_PUBLIC_IP>` (or your domain if you set one up)

### 2.3 Set your repo name in the workflow

Open [.github/workflows/deploy-frontend.yml](../.github/workflows/deploy-frontend.yml) and replace `YOUR_REPO_NAME` with your actual GitHub repository name:

```yaml
VITE_BASE: /your-actual-repo-name/
```

### 2.4 Push and deploy

```bash
git add .
git commit -m "Configure deployment"
git push
```

The **Deploy Frontend to GitHub Pages** workflow runs automatically. After it finishes (~2 minutes), your frontend is live at:

```
https://<YOUR_GITHUB_USERNAME>.github.io/<YOUR_REPO_NAME>/
```

---

## Keeping the backend updated

When you push code changes and want to redeploy the backend:

```bash
ssh ubuntu@<YOUR_VM_PUBLIC_IP>
cd <YOUR_REPO_NAME>
git pull
docker compose up -d --build
```

The AI model cache is stored in a Docker volume (`ai-model-cache`) so models are not re-downloaded on rebuilds.
