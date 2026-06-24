import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { getToken } from '@/auth/session'
import AppShell from '@/components/AppShell'
import CourseWorkspacePage from '@/pages/CourseWorkspacePage'
import CourseDetailPage from '@/pages/CourseDetailPage'
import LoginPage from '@/pages/LoginPage'

function RequireAuth() {
  return getToken() ? <Outlet /> : <Navigate to="/login" replace />
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/courses" element={<CourseWorkspacePage mode="active" />} />
          <Route path="/courses/archived" element={<CourseWorkspacePage mode="archived" />} />
          <Route path="/courses/trash" element={<CourseWorkspacePage mode="trash" />} />
          <Route path="/courses/:courseId" element={<CourseDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/courses" replace />} />
    </Routes>
  )
}

export default App
