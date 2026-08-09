import { AuthenticationPanel } from '../auth/AuthenticationPanel'
import { BackendStatus } from '../components/BackendStatus'

export function App() {
  return (
    <main className="app-shell">
      <header className="masthead">
        <span className="brand-mark" aria-hidden="true">
          PF
        </span>
        <span className="brand-name">PoultryFlow</span>
      </header>

      <section className="welcome" aria-labelledby="page-title">
        <p className="eyebrow">Farm operations</p>
        <h1 id="page-title">PoultryFlow</h1>
        <p className="description">
          A dependable farm management platform for connected poultry
          operations.
        </p>
        <div className="application-statuses">
          <AuthenticationPanel />
          <BackendStatus />
        </div>
      </section>
    </main>
  )
}
