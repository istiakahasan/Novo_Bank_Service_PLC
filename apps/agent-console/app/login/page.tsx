import { LoginForm } from '@novo/shared';

export default function LoginPage() {
  return (
    <LoginForm
      title="Novo Bank Agent Console"
      subtitle="Staff sign-in only"
      redirectTo="/dashboard"
    />
  );
}
