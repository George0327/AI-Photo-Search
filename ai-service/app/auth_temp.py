import os
import google_auth_oauthlib.flow
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import RedirectResponse
from starlette.middleware.sessions import SessionMiddleware

# Create the router
router = APIRouter(tags=["Temporary Auth"])

# For local HTTP testing only
os.environ['OAUTHLIB_INSECURE_TRANSPORT'] = '1'

CLIENT_SECRETS_FILE = "client_secret.json"
SCOPES = ['https://www.googleapis.com/auth/drive.readonly'] 
REDIRECT_URI = 'http://localhost:8000/auth/callback'

@router.get("/login")
def login(request: Request):
    flow = google_auth_oauthlib.flow.Flow.from_client_secrets_file(
        CLIENT_SECRETS_FILE, scopes=SCOPES
    )
    flow.redirect_uri = REDIRECT_URI
    authorization_url, state = flow.authorization_url(
        access_type='offline',
        prompt='consent'
    )
    request.session['state'] = state
    return RedirectResponse(url=authorization_url)

@router.get("/auth/callback")
def auth_callback(request: Request, state: str, code: str):
    if state != request.session.get('state'):
        raise HTTPException(status_code=400, detail="State mismatch")

    flow = google_auth_oauthlib.flow.Flow.from_client_secrets_file(
        CLIENT_SECRETS_FILE, scopes=SCOPES, state=state
    )
    flow.redirect_uri = REDIRECT_URI
    flow.fetch_token(code=code)
    
    credentials = flow.credentials
    print(f"\n--- REFRESH TOKEN: {credentials.refresh_token} ---\n")

    return {"refresh_token": credentials.refresh_token, "status": "Success! Check terminal."}