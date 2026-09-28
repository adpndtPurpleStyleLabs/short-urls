package com.preonsurl.coreconfig.constants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoreConfigKeys {
    public static final class App{
        private App() {}

        public static final String SECURE_DOMAIN = "sercure.domain";
        public static final String APP_URL = "app.url";
        public static final String SECURE_URL = "secure.url";
        public static final String PSECURE_URL = "psecure.url";
    }


    // Shortener
    public static final class Shortener {
        private Shortener() {}

        public static final String WORKER_COUNT = "worker.count";
        public static final String BUCKET_CAPACITY = "worker.bucket.capacity";
        public static final String SECRET = "worker.secret";
    }

    // Security
    public static final class Security {
        private Security() {}

        public static final String JWT_EXPIRATION_SECONDS = "security.jwt.expiry.seconds";
        public static final String JWT_SECRET = "security.jwt.secret";
    }

    // Email
    public static final class Email {
        private Email() {}

        public static final String API_KEY = "mailtrap.api.key";
            public static final String EMAIL = "mailtrap.email";
            public static final String NAME = "mailtrap.name";
            public static final String SUPPORT_EMAIL = "email.support";
    }

    // Email
    public static final class Endpoint {
        private Endpoint() {}

        public static final String CONSOLE = "endpoint.console";
        public static final String HELP = "endpoint.help";
        public static final String DOC = "endpoint.doc";
    }

    // Razorpay
    public static final class Razorpay {
        private Razorpay() {}

        public static final String ID = "razorpay.id";
        public static final String SECRET = "razorpay.secret";
        public static final String CURRENCY = "razorpay.currency";
        public static final String PRO_PLAN_AMOUNT = "razorpay.pro-plan-amount";
    }
}