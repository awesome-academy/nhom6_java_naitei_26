import { BookingDetailView } from "@/components/booking/booking-detail-view"

export default async function BookingDetailPage({
  params,
}: {
  params: Promise<{ publicId: string }>
}) {
  const { publicId } = await params

  return <BookingDetailView publicId={publicId} />
}
