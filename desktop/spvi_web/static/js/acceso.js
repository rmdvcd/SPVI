'use strict';
/* Pantalla de acceso: solo aparece si se creó una clave en Ajustes > Clave de acceso. */
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('login'), error = document.getElementById('login-error');
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button'); button.disabled = true; error.textContent = '';
    try {
      const response = await fetch('/login', {method: 'POST', headers: {'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf]').content},
        body: JSON.stringify({password: form.elements.password.value})});
      if (response.ok) { location.reload(); return; }
      const result = await response.json().catch(() => ({}));
      error.textContent = result.error || 'Clave incorrecta.';
      form.elements.password.select();
    } catch { error.textContent = 'SPVI no responde. Comprueba que sigue abierto.'; }
    finally { button.disabled = false; }
  });
});
