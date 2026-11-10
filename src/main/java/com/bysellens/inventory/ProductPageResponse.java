package com.bysellens.inventory;

import java.util.List;

public record ProductPageResponse(List<ProductResponse> data, Meta meta) {
    public record Meta(int page, int limit, long total, int totalPages) {
    }
}
