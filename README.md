# KoClock

Приложение для управления самодельными часами (а также попытка освоить программирование Android в целом, и RxJava в частности🙂 )

У часов есть Bluetooth модуль SPP-CA (аналог HC-05) а также радио модуль NRF24L01 для связи с другими устройствами (другие часы, внешний термометр, датчики протечки, модули управления батареями отопления и т.д.).

На данный момент реализованы следующие команды по Bluetooth:
1. "$1 <unixtime в hex формате> \r\n" – установить время. В ответ придет "Set Time OK\r\n"
2. "$2 <уровень свечения индикаторов днем> <уровень свечения индикаторов ночью> <начало ночи, например 22:00> <конец ночи, например 7:00> \r\n" – установить настройки дневного и ночного режима свечения индикаторов. В ответ придет "Save Settings OK\r\n"
3. "$3 \r\n" — получить настройки. В ответ придет "<уровень свечения индикаторов днем> <уровень свечения индикаторов ночью> <начало ночи, unixtime в dec формате> <конец ночи, unixtime в dec формате>\r\n"

В дальнейшем планирую добавить получение температуры самих часов, а также методы управления внешними модулями. Также не сделаны разметки для горизонтальной ориентации экрана.

Приложение состоит из главной активности, двух фрагментов и синглтона Bluetooth построенного на RxJava. Синглтон инициализируется из главной активности, далее добавляется фрагмент отвечающий за управление часами. В нем проверяется подключены ли часы и если нет то заменяется фрагментом подключения. 
Во фрагменте подключения отображается список ранее подключенных устройств и список с кнопкой поиска доступных устройств. Подключение осуществляется по клику элемента нужного списка, после чего возвращаюсь во фрагмент управления часами.

Все вызовы методов работы с Bluetooth асинхронны. Для работы с представлениями использовал DataBinding.