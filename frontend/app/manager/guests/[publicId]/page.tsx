import { CustomerDetail } from "@/components/admin/guests/customer-detail"

export default async function ManagerGuestDetailPage({
  params,
}: {
  params: Promise<{ publicId: string }>
}) {
  const { publicId } = await params
  return <CustomerDetail publicId={publicId} />
}
