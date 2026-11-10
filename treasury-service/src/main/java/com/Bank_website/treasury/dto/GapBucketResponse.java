package com.Bank_website.treasury.dto;

public record GapBucketResponse(
        String bucket,
        Long assetsCents,
        Long liabilitiesCents,
        Long gapCents
) {
}
