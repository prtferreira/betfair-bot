import Link from "next/link";

export default function Home() {
  return (
    <main style={{ padding: "2rem" }}>
      <h1>Betfair Trade Sim</h1>

      <Link href="/simulator">
        Go to Simulator
      </Link>
      <Link href="/scanner">
        Go to Scanner
      </Link>
    </main>
  );
}
