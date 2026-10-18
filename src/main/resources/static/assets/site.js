(() => {
  'use strict';
  const key = 'mdm-consent-v1';
  const banner = document.getElementById('cookie-consent');
  const enabled = document.querySelector('meta[name="mdm-analytics"]')?.content === 'true';
  let counted = false;
  const read = () => { try { const v = JSON.parse(localStorage.getItem(key)); return v && v.expires > Date.now() ? v : null; } catch { return null; } };
  const count = () => {
    if (!enabled || counted || read()?.analytics !== true || !['/welcome', '/privacy', '/terms'].includes(location.pathname)) return;
    const csrf = document.querySelector('meta[name="csrf-token"]')?.content;
    const header = document.querySelector('meta[name="csrf-header"]')?.content;
    if (!csrf || !header) return;
    counted = true;
    fetch('/analytics/pageview', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', [header]: csrf, 'X-MDM-Consent': 'analytics-v1' }, body: JSON.stringify({ page: location.pathname }) }).catch(() => {});
  };
  const choose = analytics => {
    try { localStorage.setItem(key, JSON.stringify({ analytics, expires: Date.now() + 180 * 86400000 })); } catch { /* Denied browser storage means the preference is not persisted. */ }
    if (banner) banner.hidden = true;
    if (analytics) count();
  };
  if (banner) banner.hidden = read() !== null;
  document.getElementById('consent-essential')?.addEventListener('click', () => choose(false));
  document.getElementById('consent-analytics')?.addEventListener('click', () => choose(true));
  document.getElementById('cookie-preferences')?.addEventListener('click', () => {
    try { localStorage.removeItem(key); } catch { }
    if (banner) { banner.hidden = false; document.getElementById('consent-essential')?.focus(); }
  });
  count();
  document.querySelectorAll('[data-permanent-disabled]').forEach(button => { if (!button.disabled) button.removeAttribute('data-permanent-disabled'); });
  document.querySelectorAll('form').forEach(form => {
    form.addEventListener('submit', event => {
      if (!form.checkValidity()) { event.preventDefault(); form.reportValidity(); return; }
      if (form.method.toLowerCase() === 'post') form.querySelectorAll('button[type="submit"],button:not([type])').forEach(button => { button.disabled = true; });
    });
  });
  window.addEventListener('pageshow', () => document.querySelectorAll('form button:disabled').forEach(button => { if (!button.hasAttribute('data-permanent-disabled')) button.disabled = false; }));
})();
