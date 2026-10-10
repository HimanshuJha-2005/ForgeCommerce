import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorState, Loading } from '../components/ui';

export function ProductDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [qty, setQty] = useState(1);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    api(`/api/products/${id}`, { auth: false }).then(setProduct).catch((e) => setError(e.message));
  }, [id]);

  async function addToCart() {
    setError('');
    setNotice('');
    if (!user) return navigate('/login');
    try {
      await api('/api/cart/items', { method: 'POST', body: { productId: Number(id), quantity: qty } });
      setNotice(`Added ${qty} to cart.`);
    } catch (err) {
      setError(err.message);
    }
  }

  if (error && !product) return <ErrorState message={error} />;
  if (!product) return <Loading />;

  return (
    <div className="card detail">
      <h2>{product.name}</h2>
      <p className="muted">{product.categoryName}</p>
      <p>{product.description || 'No description.'}</p>
      <p className="price">₹{product.price}</p>
      <p className={product.stock > 0 ? 'in-stock' : 'out-stock'}>
        {product.stock > 0 ? `${product.stock} in stock` : 'Out of stock'}
      </p>
      {error && <p className="form-error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}
      <div className="row">
        <input
          type="number"
          min={1}
          max={Math.max(product.stock, 1)}
          value={qty}
          onChange={(e) => setQty(Math.max(1, Number(e.target.value)))}
        />
        <button onClick={addToCart} disabled={product.stock === 0}>
          Add to cart
        </button>
      </div>
    </div>
  );
}
