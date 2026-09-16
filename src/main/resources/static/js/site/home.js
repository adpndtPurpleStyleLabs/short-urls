/**
 * PreonsURL Homepage Interactive Behaviors
 * Pure Vanilla JavaScript - No dependencies
 */

document.addEventListener('DOMContentLoaded', () => {
  // Mobile navigation drawer toggle
  const navToggle = document.getElementById('navToggle');
  const mainNav = document.getElementById('mainNav');

  if (navToggle && mainNav) {
    navToggle.addEventListener('click', () => {
      const isOpen = mainNav.classList.toggle('is-open');
      navToggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    });
  }

  // Interactive console revoke action demonstration
  const revokeBtns = document.querySelectorAll('.js-revoke-btn');
  revokeBtns.forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      const row = btn.closest('tr');
      if (!row) return;

      const statusPill = row.querySelector('.status-pill');
      if (statusPill && !statusPill.classList.contains('status-pill--revoked')) {
        statusPill.className = 'status-pill status-pill--revoked';
        statusPill.textContent = 'Revoked';
        btn.textContent = 'Revoked';
        btn.disabled = true;
        btn.style.opacity = '0.5';
        btn.style.cursor = 'not-allowed';

        // Update console metrics
        const revokedMetric = document.getElementById('metricRevocations');
        if (revokedMetric) {
          const current = parseInt(revokedMetric.textContent, 10) || 0;
          revokedMetric.textContent = current + 1;
        }
      }
    });
  });

  // Interactive copy code button
  const copyBtn = document.getElementById('copyApiBtn');
  const apiSnippet = document.getElementById('apiCodeSnippet');
  if (copyBtn && apiSnippet) {
    copyBtn.addEventListener('click', () => {
      const codeText = apiSnippet.innerText;
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(codeText).then(() => {
          const originalText = copyBtn.textContent;
          copyBtn.textContent = 'Copied';
          setTimeout(() => {
            copyBtn.textContent = originalText;
          }, 2000);
        });
      }
    });
  }
});
