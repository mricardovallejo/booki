package com.booki.service.impl;

import com.booki.domain.ReaderProfile;
import com.booki.domain.User;
import com.booki.repository.ReaderProfileRepository;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the editable generic reader profile every account starts with. */
@Component
@RequiredArgsConstructor
public class ReaderProfileProvisioner {

    static final String DEFAULT_NAME = "My reader profile";
    static final String FALLBACK_CONTEXT =
            "I use this general profile when I have not specified a subject-specific goal or learning preference.\n"
                    + "Calibrate your support from evidence in the conversation: what I ask, understand, or find "
                    + "difficult. Do not infer my age, education, intelligence, or reading ability.\n"
                    + "Help me build a clear mental model of the text. Start with the central idea and only the "
                    + "background needed to understand it. Define unfamiliar terms in context, make connections "
                    + "between ideas explicit, and use a concrete example or analogy when it adds real clarity.\n"
                    + "Begin concisely and in plain language. Increase detail, technical depth, or challenge as my "
                    + "questions and answers show that it would help. If I seem confused, change the explanation "
                    + "or example instead of merely repeating it.\n"
                    + "When checking understanding, ask one focused question at a time and use my answer to choose "
                    + "the next step. Ask a brief clarifying question only when my goal or preferred approach would "
                    + "materially change the help; otherwise start helping immediately.";

    private final ReaderProfileRepository readerProfiles;
    private final UserRepository users;

    /** Creates the owned default profile for a newly registered account. */
    @Transactional
    public ReaderProfile provisionFor(User user) {
        return createFromGeneralTemplate(user);
    }

    private ReaderProfile createFromGeneralTemplate(User user) {
        ReaderProfile template = readerProfiles
                .findFirstByUserIsNullAndDefaultProfileTrueOrderByIdAsc()
                .orElse(null);

        ReaderProfile profile = new ReaderProfile();
        profile.setUser(user);
        profile.setName(DEFAULT_NAME);
        profile.setContext(template != null ? template.getContext() : FALLBACK_CONTEXT);
        profile.setReaderLevel(template != null ? template.getReaderLevel() : null);
        profile.setDefaultProfile(false);
        profile.setReadOnly(false);

        ReaderProfile saved = readerProfiles.save(profile);
        user.setDefaultReaderProfile(saved);
        users.save(user);
        return saved;
    }
}
