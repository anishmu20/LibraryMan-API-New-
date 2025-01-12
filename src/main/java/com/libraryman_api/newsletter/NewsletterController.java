package com.libraryman_api.newsletter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = newsletterService;
    }

    // Subscribe Endpoint
    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(@RequestParam String email) {
        try {
            String result = newsletterService.subscribe(email);

            switch (result) {
                case "Invalid email format.":
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result); // 400 Bad Request

                case "Email is already subscribed.":
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(result); // 409 Conflict

                case "You have successfully subscribed!":
                    return ResponseEntity.status(HttpStatus.CREATED).body(result); // 201 Created

                case "You have successfully re-subscribed!":
                    return ResponseEntity.status(HttpStatus.OK).body(result); // 200 OK

                default:
                    return ResponseEntity.status(HttpStatus.OK).body(result); // Default 200 OK
            }
        } catch (Exception e) {
            // Handle unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing your subscription.");
        }
    }

    // Unsubscribe Endpoint
    @GetMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        try {
            String result = newsletterService.unsubscribe(token);

            switch (result) {
                case "Invalid or expired token.":
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result); // 404 Not Found

                case "You are already unsubscribed.":
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(result); // 409 Conflict

                case "You have successfully unsubscribed!":
                    return ResponseEntity.status(HttpStatus.OK).body(result); // 200 OK

                default:
                    return ResponseEntity.status(HttpStatus.OK).body(result); // Default 200 OK
            }
        } catch (Exception e) {
            // Handle unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing your unsubscription.");
        }
    }
}
