import { useEffect, useState } from 'react';
import { api } from '../api';

export function Seller() {
  const [categories, setCategories] = useState([]);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [form, setForm] = useState({ name: '', description: '', price: '', categoryId: '', active: true, stock: 0 });
  const [catName, setCatName] = useState('');

  useEffect(() => {
    Promise.all([api('/api/categories', { auth: false }), api('/api/products/analytics/summary')])
      .then(([cats, sum]) => {
        setCategories(cats);
        setSummary(sum);
      })
      .catch((e) => setError(e.message));
  }, []);

  async function createCategory(e) {
    e.preventDefault();
    setError('');
    try {
      const created = await api('/api/categories', { method: 'POST', body: { name: catName } });
      setCategories((c) => [...c, created]);
      setCatName('');
    } catch (err) {
      setError(err.message);
    }
  }

  async function createProduct(e) {
    e.preventDefault();
    setError('');
    try {
      const created = await api('/api/products', {
        method: 'POST',
        body: {
          name: form.name,
          description: form.description,
          price: Number(form.price),
          categoryId: Number(form.categoryId),
          active: form.active,
        },
      });
      await api(`/api/products/${created.id}/stock`, { method: 'PUT', body: { quantity: Number(form.stock) } });
      setForm({ name: '', description: '', price: '', categoryId: '', active: true, stock: 0 });
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <div>
      <div className="card">
        <h2>Seller dashboard</h2>
        {error && <p className="form-error">{error}</p>}
        {summary && (
          <p>
            {summary.totalProducts} products · {summary.totalStockUnits} units in stock
          </p>
        )}
      </div>

      <div className="card">
        <h3>New category (admin)</h3>
        <form className="row" onSubmit={createCategory}>
          <input value={catName} onChange={(e) => setCatName(e.target.value)} placeholder="Category name" required />
          <button type="submit">Create</button>
        </form>
      </div>

      <form className="card form" onSubmit={createProduct}>
        <h3>New product</h3>
        <label>
          Name
          <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        </label>
        <label>
          Description
          <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </label>
        <label>
          Price
          <input type="number" step="0.01" min="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} required />
        </label>
        <label>
          Category
          <select value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })} required>
            <option value="">Select…</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </label>
        <label>
          Initial stock
          <input type="number" min="0" value={form.stock} onChange={(e) => setForm({ ...form, stock: e.target.value })} />
        </label>
        <button type="submit">Create product</button>
      </form>
    </div>
  );
}
