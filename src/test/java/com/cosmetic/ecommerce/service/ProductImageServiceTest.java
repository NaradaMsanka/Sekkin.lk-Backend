package com.cosmetic.ecommerce.service;

import com.cosmetic.ecommerce.entity.Product;
import com.cosmetic.ecommerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;

class ProductImageServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CloudinaryService cloudinary = mock(CloudinaryService.class);
    private final ProductImageService service = new ProductImageService(products, cloudinary, new ObjectMapper());

    @Test void retainedAndSharedImagesAreNotDeleted() {
        Product otherProduct = new Product();
        otherProduct.setImages("[\"https://example.com/shared.webp\"]");
        when(products.findAll()).thenReturn(List.of(otherProduct));
        service.cleanup(Set.of("https://example.com/shared.webp", "https://example.com/removed.webp"));
        verify(cloudinary).deleteUnused("https://example.com/removed.webp");
        verifyNoMoreInteractions(cloudinary);
    }

    @Test void databaseLookupFailurePreventsDeletion() {
        when(products.findAll()).thenThrow(new RuntimeException("Database unavailable"));
        service.cleanup(Set.of("https://example.com/image.webp"));
        verifyNoInteractions(cloudinary);
    }

    @Test void cloudDeletionWaitsForTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            when(products.findAll()).thenReturn(List.of());
            service.cleanup(Set.of("https://example.com/removed.webp"));
            verifyNoInteractions(products, cloudinary);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(cloudinary).deleteUnused("https://example.com/removed.webp");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void rollbackLeavesCloudImagesUntouched() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.cleanup(Set.of("https://example.com/retained.webp"));
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(products, cloudinary);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
