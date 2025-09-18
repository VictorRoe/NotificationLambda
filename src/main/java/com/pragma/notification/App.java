package com.pragma.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class App implements RequestHandler<SQSEvent, Void> {

    private final SnsClient snsClient;
    private final String snsTopicArn;
    private final Gson gson = new Gson();

    public App() {
        this.snsClient = SnsClient.builder()
                .region(Region.of(System.getenv("REGION")))
                .build();
        this.snsTopicArn = System.getenv("SNS_TOPIC_ARN");
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        for (SQSEvent.SQSMessage msg : sqsEvent.getRecords()) {
            try {
                String messageBody = msg.getBody();
                context.getLogger().log("Mensaje recibido: " + messageBody);

                LoanData loan = gson.fromJson(messageBody, LoanData.class);


                String finalMessage = formatTextMessage(loan);
                String subject = "Decisión Sobre tu Solicitud de Préstamo";


                PublishRequest publishRequest = PublishRequest.builder()
                        .topicArn(snsTopicArn)
                        .subject(subject)
                        .message(finalMessage)
                        .build();

                snsClient.publish(publishRequest);
                context.getLogger().log("Notificación de texto plano enviada para el email: " + loan.email);

            } catch (Exception e) {
                context.getLogger().log("Error procesando mensaje: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private String formatTextMessage(LoanData loan) {
        String decision = "RECHAZADA";
        String introMessage = String.format(
                "Estimado/a %s,\n\nLamentamos informarte que tu solicitud de préstamo ha sido RECHAZADA.",
                loan.email);

        if ("APPROVED".equalsIgnoreCase(loan.status.name)) {
            decision = "APROBADA";
            introMessage = String.format(
                    "¡Felicidades, %s!\n\nTu solicitud de préstamo ha sido APROBADA.",
                    loan.email);
        }


        return String.join("\n\n",
                introMessage,
                "--- Resumen de la Solicitud ---",
                "ID de Solicitud: " + loan.id,
                "Monto: " + String.format("%,d", loan.amount),
                "Decisión Final: " + decision,
                "---------------------------------",
                "Gracias por confiar en nosotros.",
                "El Equipo de CrediYa"
        );
    }


    private static class LoanData {
        long id;
        String email;
        long amount;
        StatusData status;
    }

    private static class StatusData {
        String name;
    }
}