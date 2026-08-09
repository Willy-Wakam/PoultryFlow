import { useAuth } from './useAuth'

const genericAuthenticationError =
  'Sign-in could not be completed. Please try again.'

export function AuthenticationPanel() {
  const authentication = useAuth()

  if (authentication.status === 'initializing') {
    return (
      <p className="authentication-status" role="status">
        Checking sign-in status...
      </p>
    )
  }

  if (authentication.status === 'authenticated') {
    return (
      <div className="authentication-panel">
        <p>
          Signed in
          {authentication.username ? ` as ${authentication.username}` : ''}.
        </p>
        <button type="button" onClick={() => void authentication.logout()}>
          Sign out
        </button>
      </div>
    )
  }

  return (
    <div className="authentication-panel">
      {authentication.status === 'error' ? (
        <p className="authentication-error" role="alert">
          {genericAuthenticationError}
        </p>
      ) : (
        <p>Sign in through PoultryFlow's secure identity service.</p>
      )}
      <button type="button" onClick={() => void authentication.login()}>
        Sign in
      </button>
    </div>
  )
}
