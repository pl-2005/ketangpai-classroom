import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './components/Login/Login'
import Register from './components/Register/Register'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
    </Routes>
  )
}

export default App
