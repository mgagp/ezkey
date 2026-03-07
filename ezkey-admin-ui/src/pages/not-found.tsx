import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  return (
    <div className="min-h-screen bg-bg flex items-center justify-center p-4">
      <div className="text-center">
        <p className="font-black leading-none text-fg/10" style={{ fontSize: '10rem' }}>
          404
        </p>
        <h1 className="text-2xl font-black text-fg mt-2">Page Not Found</h1>
        <p className="text-fg-muted mt-2 mb-6">The page you&apos;re looking for doesn&apos;t exist.</p>
        <Link to="/dashboard">
          <Button>Back to Dashboard</Button>
        </Link>
      </div>
    </div>
  );
}
