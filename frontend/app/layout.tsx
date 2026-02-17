import type { Metadata } from "next";
import './styles.css'


export const metadata: Metadata = {
  title: "Betfair Trade Sim",
  description: "Trading simulator"
};

export default function RootLayout({
  children
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
