function LoginPage() {
  return (
    <main className="login-shell">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="brand login-brand">
          <span className="brand-mark">VB</span>
          <div>
            <h1>Vibe Boot</h1>
            <p>Deployment dashboard</p>
          </div>
        </div>

        <div className="login-copy">
          <p className="eyebrow">Sign in</p>
          <h2 id="login-title">Continue with GitHub</h2>
          <p>
            Use your GitHub account to access your projects, deployments, and logs.
          </p>
        </div>

        <a className="github-login-action" href="/auth/github/login">
          Login with GitHub
        </a>
      </section>
    </main>
  )
}

export default LoginPage
