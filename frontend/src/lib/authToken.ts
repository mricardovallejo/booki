/**
 * In-memory holder for the current auth token. The Axios client reads this
 * (not `localStorage`) on every request; `AuthContext` keeps it in sync and
 * owns persistence. One module so `client.ts` and `AuthContext` share it
 * without importing each other.
 */
let token: string | null = null;

export function getAuthToken(): string | null {
  return token;
}

export function setAuthToken(next: string | null): void {
  token = next;
}
