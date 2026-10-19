(() => {
  'use strict';
  const form = document.getElementById('reviewer-login');
  if (!form) return;
  const button = form.querySelector('.reviewer-explore');
  const selectedEmail = document.getElementById('reviewer-email');
  const username = document.getElementById('reviewer-username');
  const update = () => {
    const selected = form.querySelector('input[name="reviewer-choice"]:checked');
    if (!selected) return;
    selectedEmail.textContent = selected.dataset.email;
    username.value = selected.dataset.email;
    button.textContent = `Explore as ${selected.dataset.firstName} · ${selected.dataset.roleLabel}`;
  };
  form.addEventListener('change', update);
  update();
  // Preserve the same minimum form age enforced for ordinary sign-ins.
  window.setTimeout(() => { button.removeAttribute('data-permanent-disabled'); button.disabled = false; }, 1000);
})();
