// Hand-written types of the auth endpoints, in line with the backend DTO records
// (AuthResponse, LoginRequest, RegisterRequest, UserResponse, ErrorResponse).
// Types of the other endpoints are added together with the feature that uses them.

export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export interface ErrorBody {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

/** The refresh token is not in the body: it travels in the HttpOnly cookie. */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  role: UserRole;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  surname: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface UserResponse {
  id: number;
  name: string;
  surname: string;
  email: string;
  role: UserRole;
  schoolId: number | null;
  schoolName: string | null;
  classId: number | null;
  className: string | null;
  active: boolean;
  createdAt: string;
}