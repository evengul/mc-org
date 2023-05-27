import { PropsWithChildren } from 'react';
import Navbar from '../components/layout/Navbar';
import './root.css';

export default function RootLayout({ children }: PropsWithChildren<undefined>) {
  return (
    <html lang="no-nb">
      <body>
        <Navbar />
        <main>{children}</main>
      </body>
    </html>
  );
}
