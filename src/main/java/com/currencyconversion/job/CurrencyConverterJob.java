package com.currencyconversion.job;

import com.currencyconversion.api.CurrencyConverterAPI;
import com.currencyconversion.entity.AssetCurrency;
import com.currencyconversion.exception.InternalErrorException;
import com.currencyconversion.repository.CurrencyConverterRepository;
import com.currencyconversion.utils.LoggerUtils;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.util.List;
import java.util.Map;

@Component
public class CurrencyConverterJob {

  private final Logger logger = LoggerUtils.loggerFor(this);
  private final CurrencyConverterAPI api;
  private final CurrencyConverterRepository repository;

  public CurrencyConverterJob(CurrencyConverterAPI api, CurrencyConverterRepository repository) {
    this.api = api;
    this.repository = repository;
  }

  @Scheduled(fixedDelay = 1800000)
  public void run() {
    Response<Map<String, AssetCurrency>> response;
    try {
      response = api.all().execute();
    } catch (Exception exception) {
      var error = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
      logger.error("Error when execute the request. Error: {}", error);
      throw new InternalErrorException(error.getDetail());
    }

    if (response.isSuccessful()) {
      Map<String, AssetCurrency> assetCurrencyMap = response.body();

      if (assetCurrencyMap != null && !assetCurrencyMap.isEmpty()) {
        logger.info("Creating and Updating data...");

        var assets = assetCurrencyMap
          .values()
          .stream()
          .map(asset -> {
            var savedAsset = repository.getByTimestamp(asset.getTimestamp());

            if (savedAsset == null) {
              logger.info("Saving a new currency type... Type: {}", asset.getCode());
              return repository.save(asset);
            } else {
              logger.info("Updating a currency... Type: {}", savedAsset.getCode());
              return repository.save(savedAsset.copy(asset));
            }
          }).toList();

        List<String> toString = assets.stream().map(AssetCurrency::toString).toList();
        logger.info("Data created and updated successfully! Changes in: {}", String.join("\n", toString));
      }
    }
  }
}