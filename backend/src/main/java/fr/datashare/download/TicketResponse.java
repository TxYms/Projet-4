package fr.datashare.download;

/**
 * Réponse de {@code POST /api/download/{token}/unlock}.
 *
 * @param ticket ticket JWT à passer en paramètre {@code ?ticket=} de l'URL de téléchargement
 */
public record TicketResponse(String ticket) {
}
