// 전역 인증 컨텍스트 — Firebase Auth(Google) 로그인 상태와 닉네임을 제공.
"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged,
  type User,
} from "firebase/auth";
import { getFirebaseAuth } from "@/lib/firebase";
import { ensureNickname } from "@/lib/user";

interface AuthState {
  user: User | null;
  nickname: string | null;
  loading: boolean;
  signIn: () => Promise<void>;
  logout: () => Promise<void>;
  refreshNickname: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [nickname, setNickname] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const unsub = onAuthStateChanged(getFirebaseAuth(), async (u) => {
      setUser(u);
      if (u) {
        try {
          setNickname(await ensureNickname(u.uid));
        } catch {
          setNickname(null);
        }
      } else {
        setNickname(null);
      }
      setLoading(false);
    });
    return unsub;
  }, []);

  async function signIn() {
    const provider = new GoogleAuthProvider();
    await signInWithPopup(getFirebaseAuth(), provider);
  }

  async function logout() {
    await signOut(getFirebaseAuth());
  }

  async function refreshNickname() {
    if (user) setNickname(await ensureNickname(user.uid));
  }

  return (
    <AuthContext.Provider
      value={{ user, nickname, loading, signIn, logout, refreshNickname }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
