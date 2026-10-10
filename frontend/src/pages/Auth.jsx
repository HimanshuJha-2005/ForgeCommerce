import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ApiError } from '../api';

function AuthForm({ mode }) {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('CUSTOMER');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    if (!email.includes('@')) return setError('Enter a valid email.');
    if (password.length < 8) return setError('Password must be at least 8 characters.');
    setBusy(true);
    try {
      if (mode === 'login') await login(email, password);
      else await register(email, password, role);
      navigate('/');
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'Invalid email or password.' : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form" onSubmit={onSubmit}>
      <h2>{mode === 'login' ? 'Login' : 'Create account'}</h2>
      {error && <p className="form-error">{error}</p>}
      <label>
        Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>
        Password
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={8}
        />
      </label>
      {mode === 'register' && (
        <label>
          Role
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option value="CUSTOMER">Customer</option>
            <option value="SELLER">Seller</option>
            <option value="ADMIN">Admin</option>
          </select>
        </label>
      )}
      <button type="submit" disabled={busy}>
        {busy ? 'Please wait…' : mode === 'login' ? 'Login' : 'Register'}
      </button>
      {mode === 'login' ? (
        <p>
          No account? <Link to="/register">Register</Link>
        </p>
      ) : (
        <p>
          Have an account? <Link to="/login">Login</Link>
        </p>
      )}
    </form>
  );
}

export function Login() {
  return <AuthForm mode="login" />;
}

export function Register() {
  return <AuthForm mode="register" />;
}
