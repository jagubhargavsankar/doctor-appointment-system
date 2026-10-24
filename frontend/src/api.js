const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export async function api(path, options = {}) {
  const saved = sessionStorage.getItem('carepoint.session');
  let token = '';
  try {
    token = saved ? JSON.parse(saved)?.accessToken || '' : '';
  } catch {
    sessionStorage.removeItem('carepoint.session');
  }

  const headers = new Headers(options.headers || {});
  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    throw new Error('Cannot reach the backend. Make sure Spring Boot is running on port 8080.');
  }

  const text = await response.text();
  let payload = null;
  if (text) {
    try { payload = JSON.parse(text); } catch { payload = text; }
  }

  if (!response.ok) {
    const message = payload?.message || payload?.detail || payload?.error || (typeof payload === 'string' ? payload : 'Request failed');
    if (response.status === 401) {
      throw new Error('Your session may have expired. Please sign in again.');
    }
    if (response.status === 403) {
      throw new Error('This account does not have permission to perform that action.');
    }
    throw new Error(message);
  }
  return payload;
}

export const jsonBody = (value) => JSON.stringify(value);

export function displayDate(value) {
  if (!value) return 'Date not available';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString([], {
    dateStyle: 'medium', timeStyle: 'short'
  });
}

export function initials(value = '') {
  return value.trim().split(/\s+/).slice(0, 2).map((part) => part[0] || '').join('').toUpperCase() || 'DR';
}

export function money(value) {
  const number = Number(value);
  return Number.isFinite(number) ? new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', maximumFractionDigits: 0
  }).format(number) : 'Fee unavailable';
}
