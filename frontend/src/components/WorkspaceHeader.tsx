import type { LoadState } from '../types'

type WorkspaceHeaderProps = {
  loadState: LoadState
}

function WorkspaceHeader({ loadState }: WorkspaceHeaderProps) {
  return (
    <header className="topbar">
      <div>
        <p className="eyebrow">Workspace</p>
        <h2>Projects at hand</h2>
      </div>
      <span className={`status-pill ${loadState}`}>
        {loadState === 'ready'
          ? 'API connected'
          : loadState === 'loading'
            ? 'Loading API'
            : 'API unavailable'}
      </span>
    </header>
  )
}

export default WorkspaceHeader
