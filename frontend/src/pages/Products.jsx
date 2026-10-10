import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { EmptyState, ErrorState, Loading, Pagination } from '../components/ui';

export function Products() {
  const [data, setData] = useState(null);
  const [categories, setCategories] = useState([]);
  const [query, setQuery] = useState('');
  const [committedQuery, setCommittedQuery] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [sort, setSort] = useState('id,asc');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const params = new URLSearchParams({ page, size: 9, sort });
      if (committedQuery) params.set('query', committedQuery);
      if (categoryId) params.set('categoryId', categoryId);
      const [products, cats] = await Promise.all([
        api(`/api/products?${params}`, { auth: false }),
        categories.length ? Promise.resolve(categories) : api('/api/categories', { auth: false }),
      ]);
      setData(products);
      setCategories(cats);
    } catch (err) {
      setError(err.message);
    }
  }, [page, sort, committedQuery, categoryId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    load();
  }, [load]);

  function search(e) {
    e.preventDefault();
    setPage(0);
    setCommittedQuery(query.trim());
  }

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!data) return <Loading />;

  return (
    <div>
      <form className="toolbar" onSubmit={search}>
        <input
          placeholder="Search products…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }}>
          <option value="id,asc">Newest</option>
          <option value="price,asc">Price ↑</option>
          <option value="price,desc">Price ↓</option>
          <option value="name,asc">Name A–Z</option>
        </select>
        <button type="submit">Search</button>
      </form>

      {data.content.length === 0 ? (
        <EmptyState message="No products found. Try a different search." />
      ) : (
        <div className="grid">
          {data.content.map((p) => (
            <Link key={p.id} to={`/products/${p.id}`} className="card product">
              <h3>{p.name}</h3>
              <p className="muted">{p.categoryName}</p>
              <p className="price">₹{p.price}</p>
              <p className={p.stock > 0 ? 'in-stock' : 'out-stock'}>
                {p.stock > 0 ? `${p.stock} in stock` : 'Out of stock'}
              </p>
            </Link>
          ))}
        </div>
      )}
      <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />
    </div>
  );
}
