import { LoginForm } from '@novo/shared';

export default function LoginPage() {
  return (
    <LoginForm
      title="Novo Bank"
      subtitle="Sign in to your account"
      redirectTo="/dashboard"
    />
  );
}
