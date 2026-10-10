import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { EmptyState, ErrorState, Loading, Pagination } from '../components/ui';

export function Orders() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      setData(await api(`/api/orders?page=${page}&size=10`));
    } catch (err) {
      setError(err.message);
    }
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!data) return <Loading />;
  if (data.content.length === 0) return <EmptyState message="No orders yet." />;

  return (
    <div className="card">
      <h2>Order history</h2>
      <table className="table">
        <thead>
          <tr>
            <th>#</th>
            <th>Status</th>
            <th>Total</th>
            <th>Payment</th>
          </tr>
        </thead>
        <tbody>
          {data.content.map((o) => (
            <tr key={o.id}>
              <td>
                <Link to={`/orders/${o.id}`}>Order {o.id}</Link>
              </td>
              <td>{o.status}</td>
              <td>₹{o.total}</td>
              <td>{o.paymentStatus}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />
    </div>
  );
}
