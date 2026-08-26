import { ReviewSubmissionPage } from "@/components/review/review-submission-page"

export default async function ReviewPage({
  params,
}: {
  params: Promise<{ publicId: string }>
}) {
  const { publicId } = await params

  return <ReviewSubmissionPage bookingPublicId={publicId} />
}
