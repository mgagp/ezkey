package org.ezkey.integration;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class EzkeyIntegrationService {
	private final EzkeyIntegrationMapper mapper;

	public EzkeyIntegrationService(EzkeyIntegrationMapper mapper) {
		this.mapper = mapper;
	}

	public EzkeyIntegration getById(Integer id) {
		return mapper.findById(id);
	}

	public List<EzkeyIntegration> getAll() {
		return mapper.findAll();
	}

	public EzkeyIntegrationCreateResponse create(EzkeyIntegration app) {
		// Ensure application_active is set to true by default if not provided
		app.setActive(true);
		// Ensure createdAt is set to now if not provided
		if (app.getCreatedAt() == null) {
			app.setCreatedAt(java.time.LocalDateTime.now());
		}
		int result = mapper.insert(app);
		if (app.getI18n() != null) {
			for (EzkeyIntegrationI18nDto i18n : app.getI18n()) {
				i18n.setIntegrationId(app.getId());
				mapper.insertI18n(i18n);
			}
		}
		var response = new EzkeyIntegrationCreateResponse();
		response.setId(app.getId());
		return response;
	}

	public int update(EzkeyIntegration integration) {
		return mapper.update(integration);
	}

	public int delete(Integer id) {
		return mapper.delete(id);
	}

	public int deleteAll() {
		return mapper.deleteAll();
	}
}