export const PHOTOS_PER_PAGE = 20;

export const SIDEBAR_WIDTH = 300;
export const SIDEBAR_SCROLL_HEIGHT = "calc(100vh - 280px)";

// admin polling backs off towards the max while nothing changes, and resets on progress
export const ADMIN_POLL_INTERVAL_MS = 3000;
export const ADMIN_POLL_MAX_INTERVAL_MS = 15000;

export const MIN_TOP_K = 1;
export const MAX_TOP_K = 1000;

export const MAX_REFERENCE_FILES = 30;
export const MAX_REFERENCE_FILE_SIZE_MB = 15;

/** Page size used when listing users (org is domain-restricted, one page suffices). */
export const USERS_PAGE_SIZE = 200;
