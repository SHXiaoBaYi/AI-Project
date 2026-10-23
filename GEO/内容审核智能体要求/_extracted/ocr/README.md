# OCR 抽取结果

由 `BackEnd` 工具类 `com.base.admin.util.OcrGeoReviewPdfMain` 生成：

- Tess4J 5.11 + PDFBox 渲染（含 JPEG2000）
- 语言：`chi_sim+eng`，约 250dpi
- 语言包目录：`D:/AI-XBY/dev-tools/tessdata-runtime`

重跑：

```powershell
cd D:\AI-XBY\AI-Project\BackEnd
. D:\AI-XBY\dev-tools\env.ps1
mvn -s D:\AI-XBY\dev-tools\maven-settings.xml -q -DskipTests test-compile dependency:build-classpath "-Dmdep.outputFile=target\ocr-cp.txt" "-DincludeScope=test"
$cp = "target\test-classes;target\classes;" + (Get-Content target\ocr-cp.txt -Raw).Trim()
java -cp $cp com.base.admin.util.OcrGeoReviewPdfMain
```

OCR 有错字；入库前已人工校对进 `atomic-seeds.json`。冲突以撰写注意点为准。
