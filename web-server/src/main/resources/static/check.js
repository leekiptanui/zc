// Calls the web server the way the staff web app should: same origin, the session cookie sent
// automatically, the anti-CSRF header on every POST, and no token anywhere. Nothing is logged to
// the console and nothing is stored in the browser.

function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

async function post(path, body) {
  return fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken(), 'Idempotency-Key': crypto.randomUUID() },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

async function refresh() {
  const user = await (await fetch('/bff/user')).json()
  document.getElementById('signed-out').hidden = user.authenticated
  document.getElementById('signed-in').hidden = !user.authenticated
  if (user.authenticated) {
    document.getElementById('name').textContent = user.name
    document.getElementById('roles').textContent = user.roles.join(', ')
  }
  document.getElementById('result').textContent = ''
}

document.getElementById('sign-in').addEventListener('submit', async (event) => {
  event.preventDefault()
  const form = new FormData(event.target)
  const response = await post('/bff/login', { username: form.get('username'), password: form.get('password') })
  document.getElementById('sign-in-error').hidden = response.ok
  event.target.reset()
  await refresh()
})

document.getElementById('load').addEventListener('click', async () => {
  const response = await fetch('/api/v1/programmes')
  const out = document.getElementById('result')
  if (!response.ok) {
    out.textContent = `Refused (${response.status})`
    if (response.status === 401) await refresh()
    return
  }
  const envelope = await response.json()
  out.textContent = `${envelope.status}: ${envelope.data?.totalItems ?? 0} programme(s)`
})

document.getElementById('sign-out').addEventListener('click', async () => {
  await post('/bff/logout')
  await refresh()
})

refresh()
