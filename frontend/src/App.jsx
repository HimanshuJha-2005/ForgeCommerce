import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth';
import { Navbar } from './components/ui';
import { ProtectedRoute } from './components/ProtectedRoute';
import { Login, Register } from './pages/Auth';
import { Products } from './pages/Products';
import { ProductDetail } from './pages/ProductDetail';
import { Cart } from './pages/Cart';
import { Orders } from './pages/Orders';
import { OrderDetail } from './pages/OrderDetail';
import { Seller } from './pages/Seller';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Navbar />
        <main className="container">
          <Routes>
            <Route path="/" element={<Products />} />
            <Route path="/products/:id" element={<ProductDetail />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            <Route path="/cart" element={<ProtectedRoute><Cart /></ProtectedRoute>} />
            <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
            <Route path="/orders/:id" element={<ProtectedRoute><OrderDetail /></ProtectedRoute>} />
            <Route
              path="/seller"
              element={
                <ProtectedRoute roles={['SELLER', 'ADMIN']}>
                  <Seller />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<p className="state">Not found. <Link to="/">Home</Link></p>} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  );
}
