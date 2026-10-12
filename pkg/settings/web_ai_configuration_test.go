package settings

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gopkg.in/ini.v1"
)

func TestWebAIConfigurationSaveLoadAndReplace(t *testing.T) {
	config := &Config{WorkingPath: t.TempDir()}
	first := &WebAIConfiguration{OwnerUid: 123, Enabled: true, ImageEnabled: true, BaseURL: "https://api.deepseek.com", ModelID: "deepseek-chat", RequestTimeout: 60000}
	require.NoError(t, SaveWebAIConfiguration(config, first))

	loaded, err := LoadWebAIConfiguration(config)
	require.NoError(t, err)
	require.NotNil(t, loaded)
	assert.Equal(t, first, loaded)
	assert.True(t, loaded.ImageEnabled)

	first.ModelID = "deepseek-reasoner"
	require.NoError(t, SaveWebAIConfiguration(config, first))
	loaded, err = LoadWebAIConfiguration(config)
	require.NoError(t, err)
	assert.Equal(t, "deepseek-reasoner", loaded.ModelID)
	assert.FileExists(t, filepath.Join(config.WorkingPath, "data", "ai-configuration.json"))
}

func TestApplyWebAIConfigurationKeepsEnvironmentKeyWhenOverrideKeyIsEmpty(t *testing.T) {
	config := &Config{TextRecognitionLLMConfig: &LLMConfig{OpenAICompatibleAPIKey: "environment-secret"}}
	override := &WebAIConfiguration{Enabled: true, ImageEnabled: true, BaseURL: "https://api.deepseek.com/", ModelID: "deepseek-chat", EnableThinking: LLMThinkingDisabled, RequestTimeout: 120000}

	ApplyWebAIConfiguration(config, override)

	assert.True(t, config.TransactionFromAITextRecognition)
	assert.True(t, config.TransactionFromAIImageRecognition)
	require.NotNil(t, config.ReceiptImageRecognitionLLMConfig)
	assert.Equal(t, OpenAICompatibleLLMProvider, config.TextRecognitionLLMConfig.LLMProvider)
	assert.Equal(t, "https://api.deepseek.com", config.TextRecognitionLLMConfig.OpenAICompatibleBaseURL)
	assert.Equal(t, "environment-secret", config.TextRecognitionLLMConfig.OpenAICompatibleAPIKey)
	assert.Equal(t, uint32(120000), config.TextRecognitionLLMConfig.LargeLanguageModelAPIRequestTimeout)
}

func TestLoadAIConfigurationLegacyEnvironmentFallback(t *testing.T) {
	configFile := ini.Empty()
	configFile.Section("ai").Key("transaction_from_ai_text_recognition").SetValue("false")

	t.Setenv("EBK_LLM_TRANSACTION_FROM_AI_TEXT_RECOGNITION", "true")
	config := &Config{}
	assert.Nil(t, loadAIConfiguration(config, configFile, "ai", "llm"))
	assert.True(t, config.TransactionFromAITextRecognition)
	assert.False(t, config.TransactionFromAIImageRecognition)

	t.Setenv("EBK_AI_TRANSACTION_FROM_AI_TEXT_RECOGNITION", "false")
	config = &Config{}
	assert.Nil(t, loadAIConfiguration(config, configFile, "ai", "llm"))
	assert.False(t, config.TransactionFromAITextRecognition)
}
