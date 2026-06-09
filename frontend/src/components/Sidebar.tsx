function Sidebar() {
  return (
    <aside className="sidebar" aria-label="Primary">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">
          VB
        </span>
        <div>
          <h1>Vibe Boot</h1>
          <p>Local deploy dashboard</p>
        </div>
      </div>

      <nav className="nav-list">
        <a className="nav-item active" href="#projects">
          Projects
        </a>
        <a className="nav-item" href="#deployments">
          Deployments
        </a>
        <a className="nav-item" href="#logs">
          Logs
        </a>
      </nav>
    </aside>
  )
}

export default Sidebar
