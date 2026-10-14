import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ticket-on",
  description: "소규모 주최자가 좌석 배치도를 직접 만들고, 관객이 원하는 자리를 골라 예매하는 서비스",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
