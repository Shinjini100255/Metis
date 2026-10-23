class ModelUnavailableError(Exception):
    pass


def predict(_request):
    raise ModelUnavailableError("No trained model is loaded.")
