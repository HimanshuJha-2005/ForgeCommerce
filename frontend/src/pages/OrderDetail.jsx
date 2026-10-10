import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorState, Loading } from '../components/ui';

const NEXT = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['PROCESSING', 'CANCELLED'],
  PROCESSING: ['SHIPPED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};

export function OrderDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const staff = user && (user.role === 'SELLER' || user.role === 'ADMIN');

  const load = useCallback(async () => {
    setError('');
    try {
      setOrder(await api(`/api/orders/${id}`));
    } catch (err) {
      setError(err.message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  async function act(fn) {
    setError('');
    setBusy(true);
    try {
      setOrder(await fn());
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (error && !order) return <ErrorState message={error} onRetry={load} />;
  if (!order) return <Loading />;

  return (
    <div className="card">
      <h2>Order {order.id}</h2>
      <p>
        Status: <strong>{order.status}</strong> · Total: <strong>₹{order.total}</strong>
      </p>
      <p className="muted">
        Payment: {order.paymentStatus} ({order.paymentReference})
      </p>
      {error && <p className="form-error">{error}</p>}
      <table className="table">
        <thead>
          <tr>
            <th>Product</th>
            <th>Qty</th>
            <th>Price</th>
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          {order.items.map((i) => (
            <tr key={i.productId}>
              <td>{i.productName}</td>
              <td>{i.quantity}</td>
              <td>₹{i.unitPrice}</td>
              <td>₹{i.lineTotal}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="actions">
        {(order.status === 'PENDING' || order.status === 'CONFIRMED') && (
          <button disabled={busy} onClick={() => act(() => api(`/api/orders/${id}/cancel`, { method: 'POST' }))}>
            Cancel order
          </button>
        )}
        {order.status === 'PENDING' && (
          <>
            <button
              disabled={busy}
              onClick={() =>
                act(() => api(`/api/orders/${id}/payment/simulate`, { method: 'POST', body: { outcome: 'SUCCESS' } }))
              }
            >
              Simulate payment success
            </button>
            <button
              disabled={busy}
              onClick={() =>
                act(() => api(`/api/orders/${id}/payment/simulate`, { method: 'POST', body: { outcome: 'FAILED' } }))
              }
            >
              Simulate payment failure
            </button>
          </>
        )}
        {staff &&
          NEXT[order.status].map((s) => (
            <button
              key={s}
              disabled={busy}
              onClick={() => act(() => api(`/api/orders/${id}/status`, { method: 'PATCH', body: { status: s } }))}
            >
              → {s}
            </button>
          ))}
      </div>
    </div>
  );
}
