export class ApiError extends Error {
  constructor(code, status = 0, detail = {}) { super(code); this.code = code; this.status = status; Object.assign(this, detail); }
}
function tokenPair(data) {
  if (!/^[A-Za-z0-9._~-]{1,8192}$/.test(data.accessToken || '') || !/^[A-Za-z0-9_-]{1,512}$/.test(data.refreshToken || '')) throw new ApiError('INVALID_AUTH_RESPONSE');
  return { accessToken: data.accessToken, refreshToken: data.refreshToken };
}
export class ApiClient {
  constructor(fetcher = (...args) => fetch(...args)) { this.fetcher = fetcher; this.tokens = null; this.generation = 0; this.refreshing = null; this.onExpired = () => {}; }
  clear() { this.tokens = null; this.generation++; this.refreshing = null; }
  session(data) {
    this.tokens = tokenPair(data); this.generation++;
  }
  async send(method, path, body, token) {
    if (!/^\/(api\/v1\/|oauth2\/jwks$|bridge\/)/.test(path) || path.includes('..')) throw new ApiError('INVALID_API_PATH');
    let response;
    try { response = await this.fetcher(path, { method, credentials: 'omit', cache: 'no-store', headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) }); }
    catch { throw new ApiError('CONNECTION_FAILED'); }
    let data;
    try { const text = await response.text(); data = text ? JSON.parse(text) : {}; if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error(); } catch { throw new ApiError('INVALID_SERVER_RESPONSE'); }
    if (!response.ok) throw new ApiError(data.code || 'REQUEST_REJECTED', response.status, { requestId: data.requestId, fields: data.fields, remainingTries: data.remainingTries });
    return data;
  }
  async call(method, path, body) {
    const version = this.generation;
    const publicRoute = path.startsWith('/api/v1/auth/') || path.startsWith('/bridge/') || path.startsWith('/oauth2/');
    const observed = this.tokens;
    const unchanged = () => { if (version !== this.generation) throw new ApiError('SESSION_CHANGED'); };
    try { const data = await this.send(method, path, body, publicRoute ? null : observed?.accessToken); unchanged(); return data; }
    catch (error) {
      unchanged();
      if (publicRoute || error.status !== 401 || error.code !== 'INVALID_OR_REVOKED_SESSION' || !observed) throw error;
      if (this.tokens === observed && !this.refreshing) {
        const pending = this.send('POST', '/api/v1/auth/refresh', { refreshToken: observed.refreshToken }).then(data => {
          unchanged();
          this.tokens = tokenPair(data);
        }).catch(e => { if (version === this.generation) { this.clear(); this.onExpired(); } throw e; });
        this.refreshing = pending;
        pending.finally(() => { if (this.refreshing === pending) this.refreshing = null; }).catch(() => {});
      }
      if (this.refreshing) await this.refreshing;
      unchanged();
      try { const result = await this.send(method, path, body, this.tokens.accessToken); unchanged(); return result; }
      catch (e) { if (version === this.generation && e.status === 401) { this.clear(); this.onExpired(); } throw e; }
    }
  }
}
export const api = new ApiClient();
export const endpoint = (value) => encodeURIComponent(String(value));
