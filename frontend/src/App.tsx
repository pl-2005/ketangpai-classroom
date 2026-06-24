import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './components/Login/Login'
import Register from './components/Register/Register'
import CourseList from './pages/CourseList/CourseList'
import UserSettings from './pages/UserSettings/UserSettings'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/courses" element={<CourseList />} />
      <Route path="/user-settings" element={<UserSettings />} />
    </Routes>
  )
}

export default App
