import { LogOut } from 'lucide-react';
import { useAuth } from '@/context/auth-context';
import { Button } from '@/components/ui/button';
import { useNavigate } from 'react-router-dom';

interface HeaderProps {
  title: string;
}

export function Header({ title }: HeaderProps) {
  const { session, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <header className="h-13 shrink-0 border-b-2 border-fg bg-surface flex items-center justify-between px-6">
      <h1 className="text-xs font-black uppercase tracking-[0.2em] text-fg">{title}</h1>
      <div className="flex items-center gap-3">
        {session && (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-fg-muted text-xs">Signed in as</span>
            <span className="font-bold text-fg text-sm">{session.username}</span>
            <span className="text-[10px] px-1.5 py-0.5 bg-fg text-surface font-black uppercase tracking-widest">
              {session.adminType.replace('_ADMIN', '')}
            </span>
          </div>
        )}
        <Button variant="ghost" size="sm" onClick={handleLogout} className="gap-1.5">
          <LogOut className="size-3.5" />
          Logout
        </Button>
      </div>
    </header>
  );
}
