import type { LoadState } from '../types'
import type { CurrentUserResponse } from '../api'

type WorkspaceHeaderProps = {
  currentUser: CurrentUserResponse
  loadState: LoadState
  onLogout: () => void
}

function WorkspaceHeader({ currentUser, loadState, onLogout }: WorkspaceHeaderProps) {
  const displayName = currentUser.name ?? currentUser.githubUsername

  return (
    <header className="topbar">
      <div>
        <p className="eyebrow">Workspace</p>
        <h2>Projects at hand</h2>
      </div>
      <div className="topbar-actions">
        <span className="user-pill">{displayName}</span>
        <span className={`status-pill ${loadState}`}>
          {loadState === 'ready'
            ? 'API connected'
            : loadState === 'loading'
              ? 'Loading API'
              : 'API unavailable'}
        </span>
        <button className="secondary-action" onClick={onLogout} type="button">
          Logout
        </button>
      </div>
    </header>
  )
}

export default WorkspaceHeader
