import { Routes, Route, Navigate } from 'react-router-dom';
import Login from './components/Login/Login';
import Register from './components/Register/Register';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import CourseList from './pages/CourseList/CourseList';
import CourseDetail from './pages/CourseDetail/CourseDetail';
import AssignmentDetail from './pages/AssignmentDetail/AssignmentDetail';
import Grading from './pages/Grading/Grading';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/courses" replace />} />
        <Route path="courses" element={<CourseList />} />
        <Route path="courses/:courseId" element={<CourseDetail />} />
        <Route path="courses/:courseId/assignments/:assignmentId" element={<AssignmentDetail />} />
        <Route path="courses/:courseId/assignments/:assignmentId/grade/:submissionId" element={<Grading />} />
      </Route>
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

export default App;
