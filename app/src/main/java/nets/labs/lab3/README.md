# Location Search Application

## Описание проекта

Location Search Application - это десктопное приложение на Java Swing для поиска географических локаций и получения комплексной информации о них. Приложение интегрируется с несколькими внешними API для предоставления данных о местоположении, погоде и достопримечательностях.

## Функциональность

### Основные возможности:
- **Поиск локаций** по названию через Graphhopper Geocoding API
- **Отображение подробной информации** о выбранной локации
- **Погодные данные** от OpenWeather API
- **Ближайшие достопримечательности** от OpenTripMap API
- **Автоматическое определение типа локации**
- **Пользовательский интерфейс** с разделением на список результатов и детальную информацию

### Особенности:
- Асинхронные запросы к API для отзывчивого интерфейса
- Обработка ошибок и таймаутов
- Локализация на русском языке
- Конфигурируемые параметры через файл настроек

## Архитектура проекта

### Структура пакетов:
```
nets.labs.lab3/
├── services/          # Сервисные классы для работы с API
├── dto/              # Data Transfer Objects
└── AppConstants.java # Константы приложения
```

### Основные компоненты:

#### Сервисы:
- **`ApiClient`** - HTTP-клиент для выполнения запросов
- **`GeocodingService`** - работа с Graphhopper API для геокодирования
- **`WeatherService`** - получение погодных данных от OpenWeather
- **`PlacesService`** - поиск достопримечательностей через OpenTripMap
- **`LocationTypeService`** - определение типа локации

#### DTO классы:
- **`Location`** - данные о локации из Graphhopper
- **`Weather`** - погодные данные
- **`SimplePlace`** / **`PlaceDetails`** - информация о достопримечательностях
- **`CompositeResult`** - агрегированные данные для отображения

#### GUI:
- **`LocationSearchAppGUI`** - главное окно приложения
- **`AppConfig`** - управление конфигурацией

## Требования

### Системные требования:
- Java 21 или выше
- Gradle 8.8+

### Внешние API ключи:
Для работы приложения необходимы API ключи от:
- **Graphhopper** - для геокодирования
- **OpenWeather** - для погодных данных  
- **OpenTripMap** - для информации о достопримечательностях

## Конфигурация

Создайте файл `src/main/resources/config.properties`:

```properties
# Graphhopper API
graphhopper.api.key=your_graphhopper_api_key
graphhopper.base.url=https://graphhopper.com/api/1/geocode

# OpenTripMap API
opentripmap.api.key=your_opentripmap_api_key
opentripmap.base.url=https://api.opentripmap.com/0.1
opentripmap.radius.default=1000
opentripmap.limit.default=10

# OpenWeather API
openweather.api.key=your_openweather_api_key
openweather.base.url=https://api.openweathermap.org/data/2.5/weather

# Application settings
app.locale=ru
app.units=metric
app.http.timeout.seconds=15
app.http.connect.timeout.seconds=10
```

## Особенности реализации

### Асинхронность:
- Использование `CompletableFuture` для неблокирующих API вызовов
- Обновление UI через `SwingUtilities.invokeLater()`

### Обработка ошибок:
- Грейсфул деградация при недоступности отдельных сервисов
- Информативные сообщения об ошибках

### Производительность:
- Таймауты на HTTP запросы
- Паузы между запросами к OpenTripMap API для соблюдения лимитов

**Важно**: Получите бесплатные API ключи на сайтах провайдеров перед использованием приложения.