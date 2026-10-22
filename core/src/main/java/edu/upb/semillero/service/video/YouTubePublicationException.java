package edu.upb.semillero.service.video;

public class YouTubePublicationException extends RuntimeException {
    private final boolean reviewRequired;

    public YouTubePublicationException(boolean reviewRequired) {
        super(reviewRequired ? "La publicación requiere revisión en YouTube antes de reintentar"
                : "No se pudo publicar en YouTube; revisa la autorización y la conexión antes de reintentar");
        this.reviewRequired = reviewRequired;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }
}
