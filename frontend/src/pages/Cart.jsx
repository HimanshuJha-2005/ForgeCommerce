import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { EmptyState, ErrorState, Loading } from '../components/ui';

export function Cart() {
  const [cart, setCart] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    try {
      setCart(await api('/api/cart'));
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function remove(productId) {
    await api(`/api/cart/items/${productId}`, { method: 'DELETE' });
    load();
  }

  async function checkout() {
    setError('');
    setBusy(true);
    try {
      const order = await api('/api/orders', {
        method: 'POST',
        body: { idempotencyKey: crypto.randomUUID() },
      });
      navigate(`/orders/${order.id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !cart) return <ErrorState message={error} onRetry={load} />;
  if (!cart) return <Loading />;
  if (cart.items.length === 0) return <EmptyState message="Your cart is empty." />;

  return (
    <div className="card">
      <h2>Cart</h2>
      {error && <p className="form-error">{error}</p>}
      <table className="table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Qty</th>
            <th>Price</th>
            <th>Total</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {cart.items.map((i) => (
            <tr key={i.productId}>
              <td>{i.productName}</td>
              <td>{i.quantity}</td>
              <td>₹{i.unitPrice}</td>
              <td>₹{i.lineTotal}</td>
              <td>
                <button onClick={() => remove(i.productId)}>Remove</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="total">Total: ₹{cart.total}</p>
      <button onClick={checkout} disabled={busy}>
        {busy ? 'Placing order…' : 'Checkout'}
      </button>
    </div>
  );
}
